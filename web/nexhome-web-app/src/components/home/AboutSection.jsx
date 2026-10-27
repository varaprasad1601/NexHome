import AppLink from '../common/AppLink';
import Icon from '../common/Icon';

/** About NexHome with vision, mission and core values. */
export default function AboutSection() {
  return (
    <section className="py-16 bg-white" id="about">
      <div className="container max-w-6xl">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10 items-center">
          {/* Left: About Text */}
          <div className="lg:col-span-5">
            <h2 className="text-3xl sm:text-4xl font-black text-[#0B438B] tracking-tight mb-4">
              About <span className="text-[#168F5B]">NexHome</span>
            </h2>
            <p className="text-sm text-gray-600 leading-relaxed mb-6">
              NexHome connects homes with the right expertise. We simplify access to reliable household services by bringing skilled professionals and customers together, making everyday home care easier, faster and more trusted.
            </p>
            <AppLink
              className="inline-flex items-center gap-2 text-xs font-bold text-[#168F5B] border border-[#168F5B] px-5 py-2.5 rounded-full hover:bg-green-50 transition"
              to="/about"
            >
              About Us <Icon name="arrow-right" size={14} />
            </AppLink>
          </div>

          {/* Right: 3 Value Tiles */}
          <div className="lg:col-span-7 grid grid-cols-1 sm:grid-cols-3 gap-4">
            {/* Vision */}
            <div className="bg-[#EFF8FF] border border-blue-100/70 rounded-2xl p-5 text-center flex flex-col items-center">
              <div className="w-12 h-12 rounded-full bg-blue-100/60 text-[#0B438B] flex items-center justify-center mb-3">
                <Icon name="eye" size={22} />
              </div>
              <h3 className="text-sm font-extrabold text-[#0B438B] mb-2">Our Vision</h3>
              <p className="text-xs text-gray-600 leading-relaxed">
                To be the most trusted home services platform in every community.
              </p>
            </div>

            {/* Mission */}
            <div className="bg-[#EDFAF1] border border-green-100/70 rounded-2xl p-5 text-center flex flex-col items-center">
              <div className="w-12 h-12 rounded-full bg-green-100/60 text-[#168F5B] flex items-center justify-center mb-3">
                <Icon name="target" size={22} />
              </div>
              <h3 className="text-sm font-extrabold text-[#168F5B] mb-2">Our Mission</h3>
              <p className="text-xs text-gray-600 leading-relaxed">
                To simplify household service access with reliable professionals and great customer experiences.
              </p>
            </div>

            {/* Core Values */}
            <div className="bg-[#F0F7FF] border border-blue-100/70 rounded-2xl p-5 text-center flex flex-col items-center">
              <div className="w-12 h-12 rounded-full bg-blue-100/60 text-[#0B438B] flex items-center justify-center mb-3">
                <Icon name="heart" size={22} />
              </div>
              <h3 className="text-sm font-extrabold text-[#0B438B] mb-2">Our Core Values</h3>
              <p className="text-xs text-gray-600 leading-relaxed">
                Trust, Quality, Transparency, Community Growth and Customer Satisfaction.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
